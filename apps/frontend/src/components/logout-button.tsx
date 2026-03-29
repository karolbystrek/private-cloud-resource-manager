'use client';

import { RiLogoutBoxRLine } from '@remixicon/react';
import { useRouter } from 'next/navigation';
import { useState } from 'react';

import { Button } from '@/components/ui/button';
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';

export function LogoutButton() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');

  async function handleLogout() {
    setErrorMessage('');
    setIsSubmitting(true);

    try {
      const response = await fetch('/api/auth/logout', {
        method: 'POST',
      });

      if (!response.ok) {
        setErrorMessage('Logout failed. Please try again.');
        return;
      }

      setOpen(false);
      router.replace('/login');
      router.refresh();
    } catch {
      setErrorMessage('Logout failed. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleOpenChange(nextOpen: boolean) {
    if (isSubmitting) {
      return;
    }

    setOpen(nextOpen);
    if (!nextOpen) {
      setErrorMessage('');
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger asChild>
        <Button variant="outline" size="icon" aria-label="Logout">
          <RiLogoutBoxRLine />
        </Button>
      </AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Log out</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to log out?
          </AlertDialogDescription>
        </AlertDialogHeader>
        {errorMessage
          ? <p className="text-destructive text-xs">{errorMessage}</p>
          : null}
        <AlertDialogFooter>
          <AlertDialogCancel asChild>
            <Button variant="outline" disabled={isSubmitting}>Cancel</Button>
          </AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={handleLogout}
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Logging out...' : 'Logout'}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
